import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useCreateWorkspace } from "./hooks";
import { ApiError } from "@/types/api";
import { slugify } from "@/lib/utils";

// ── Zod schema — mirrors backend CreateWorkspaceRequest constraints ───────────

const schema = z.object({
  name: z
    .string()
    .min(1, "Name is required")
    .max(160, "Name must be 160 characters or fewer"),
  slug: z
    .string()
    .min(1, "Slug is required")
    .max(160, "Slug must be 160 characters or fewer")
    .regex(/^[a-z0-9-]+$/, "Slug may only contain lowercase letters, numbers, and hyphens"),
  description: z
    .string()
    .max(2000, "Description must be 2000 characters or fewer")
    .optional(),
});

type FormValues = z.infer<typeof schema>;

interface CreateWorkspaceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function CreateWorkspaceDialog({
  open,
  onOpenChange,
}: CreateWorkspaceDialogProps) {
  const mutation = useCreateWorkspace();

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", slug: "", description: "" },
  });

  const nameValue = watch("name");

  // Auto-derive slug from name while the user is typing
  useEffect(() => {
    setValue("slug", slugify(nameValue), { shouldValidate: false });
  }, [nameValue, setValue]);

  // Reset form when dialog closes
  useEffect(() => {
    if (!open) {
      reset();
      mutation.reset();
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, reset]);

  async function onSubmit(values: FormValues) {
    try {
      await mutation.mutateAsync({
        name: values.name,
        slug: values.slug,
        description: values.description || undefined,
      });
      onOpenChange(false);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.isValidation()) {
          const map = err.violationMap();
          (Object.entries(map) as [keyof FormValues, string][]).forEach(
            ([field, msg]) => setError(field, { message: msg })
          );
        }
        // 409 conflict — slug already taken
        if (err.status === 409) {
          setError("slug", { message: "This slug is already taken. Try another." });
        }
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create workspace</DialogTitle>
          <DialogDescription>
            A workspace is a shared space for your team's assets and documents.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="space-y-4">
            {/* Name */}
            <div className="space-y-1.5">
              <Label htmlFor="ws-name">Name</Label>
              <Input
                id="ws-name"
                placeholder="e.g. Product Design"
                autoComplete="off"
                aria-describedby={errors.name ? "ws-name-error" : undefined}
                aria-invalid={!!errors.name}
                {...register("name")}
              />
              {errors.name && (
                <p id="ws-name-error" className="text-xs text-destructive" role="alert">
                  {errors.name.message}
                </p>
              )}
            </div>

            {/* Slug */}
            <div className="space-y-1.5">
              <Label htmlFor="ws-slug">
                Slug
                <span className="ml-1 text-xs text-muted-foreground">(URL identifier)</span>
              </Label>
              <Input
                id="ws-slug"
                placeholder="e.g. product-design"
                autoComplete="off"
                aria-describedby={errors.slug ? "ws-slug-error" : "ws-slug-hint"}
                aria-invalid={!!errors.slug}
                {...register("slug")}
              />
              <p id="ws-slug-hint" className="text-xs text-muted-foreground">
                Lowercase letters, numbers, and hyphens only.
              </p>
              {errors.slug && (
                <p id="ws-slug-error" className="text-xs text-destructive" role="alert">
                  {errors.slug.message}
                </p>
              )}
            </div>

            {/* Description */}
            <div className="space-y-1.5">
              <Label htmlFor="ws-description">
                Description
                <span className="ml-1 text-xs text-muted-foreground">(optional)</span>
              </Label>
              <Input
                id="ws-description"
                placeholder="What is this workspace for?"
                aria-describedby={errors.description ? "ws-desc-error" : undefined}
                aria-invalid={!!errors.description}
                {...register("description")}
              />
              {errors.description && (
                <p id="ws-desc-error" className="text-xs text-destructive" role="alert">
                  {errors.description.message}
                </p>
              )}
            </div>

            {/* Top-level API error */}
            {mutation.error instanceof ApiError &&
              !mutation.error.isValidation() &&
              mutation.error.status !== 409 && (
                <p className="text-sm text-destructive" role="alert">
                  {mutation.error.message}
                </p>
              )}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting || mutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting || mutation.isPending}
            >
              {(isSubmitting || mutation.isPending) && (
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
              )}
              Create workspace
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
